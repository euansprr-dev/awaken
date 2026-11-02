package dev.awaken.bells.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.bells.FoundBellBlock;
import dev.awaken.bells.FoundBellEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a bell lying where it was won (SPEC §43.16): hanging under its branch, stirring a little in the air, or sat mouth
 * down on the floor where it fell.
 */
public final class FoundBellRenderer implements BlockEntityRenderer<FoundBellEntity, FoundBellRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        boolean has, hanging, great;
        float age;
    }

    private final ItemModelResolver items;

    public FoundBellRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FoundBellEntity be, State state, float partialTicks, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, crumbling);
        int no = be.no();
        state.has = no >= 0 && no < 100 && be.getLevel() != null;
        if (!state.has) {
            state.item.clear();
            return;
        }
        state.hanging = be.getBlockState().getValue(FoundBellBlock.HANGING);
        var src = dev.awaken.bells.BellCatalog.of(no).source();
        state.great = src == dev.awaken.bells.BellCatalog.Source.PROVING || src == dev.awaken.bells.BellCatalog.Source.SEED;
        state.age = be.getLevel().getGameTime() + partialTicks;
        items.updateForTopItem(state.item, HooksRenderer.stack(no), ItemDisplayContext.NONE, be.getLevel(), null, (int) be.getBlockPos().asLong());
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (!state.has) return;
        pose.pushPose();
        if (state.hanging) {
            pose.translate(0.5F, 1F, 0.5F);
            pose.rotateDegrees(Axis.XP, (float) Math.sin(state.age * 0.05F) * 3F);
            pose.rotateDegrees(Axis.ZP, (float) Math.sin(state.age * 0.037F) * 2F);
            pose.scale(0.55F, 0.55F, 0.55F);
            pose.translate(0F, -7F / 16F, 0F);
        } else {
            // sat on the floor, mouth down: the model's lip (y 1.4) on the ground
            pose.translate(0.5F, 0F, 0.5F);
            pose.rotateDegrees(Axis.YP, 23F);
            // a Great Bell sat in the yard is as big as it is
            float k = state.great ? 1.5F : 0.7F;
            pose.scale(k, k, k);
            pose.translate(0F, 0.5F - 1.4F / 16F, 0F);
        }
        state.item.submit(pose, out, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(FoundBellEntity be) {
        // a Great Bell sat on the ground stands taller and wider than its block
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(0.5, 0, 0.5).expandTowards(0, 1, 0);
    }
}
