package dev.awaken.hearth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.hearth.block.TrophyMountBlock;
import dev.awaken.hearth.block.TrophyMountBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Shows the hung trophy in front of its plaque, a little larger than an item frame would. */
public final class TrophyMountRenderer implements BlockEntityRenderer<TrophyMountBlockEntity, TrophyMountRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        boolean has;
        Direction facing = Direction.NORTH;
    }

    private final ItemModelResolver items;

    public TrophyMountRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TrophyMountBlockEntity be, State state, float partialTicks, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, crumbling);
        state.facing = be.getBlockState().getValue(TrophyMountBlock.FACING);
        state.has = !be.isEmpty();
        if (state.has) items.updateForTopItem(state.item, be.trophy(), ItemDisplayContext.FIXED, be.getLevel(), null, (int) be.getBlockPos().asLong());
        else state.item.clear();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (!state.has) return;
        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        pose.rotateDegrees(Axis.YP, 180f - state.facing.toYRot());
        pose.translate(0f, 0f, 0.34f);
        pose.scale(0.72f, 0.72f, 0.72f);
        state.item.submit(pose, out, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
