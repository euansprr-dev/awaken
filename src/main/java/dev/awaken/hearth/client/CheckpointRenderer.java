package dev.awaken.hearth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.hearth.block.CheckpointRingBlock;
import dev.awaken.hearth.block.CheckpointRingBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The ring's place on its course, floating in the middle of the ring (readable from both sides). */
public final class CheckpointRenderer implements BlockEntityRenderer<CheckpointRingBlockEntity, CheckpointRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        Direction.Axis axis = Direction.Axis.Z;
        @Nullable FormattedCharSequence label;
        int width;
    }

    private final Font font;

    public CheckpointRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CheckpointRingBlockEntity be, State state, float partialTicks, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, crumbling);
        state.axis = be.getBlockState().getValue(CheckpointRingBlock.AXIS);
        if (be.index() <= 0) {
            state.label = null;
            return;
        }
        state.label = Component.literal(String.valueOf(be.index())).getVisualOrderText();
        state.width = font.width(state.label);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (state.label == null) return;
        for (int side = 0; side < 2; side++) {
            pose.pushPose();
            pose.translate(0.5f, 0.5f, 0.5f);
            pose.rotateDegrees(Axis.YP, (state.axis == Direction.Axis.X ? 90f : 0f) + side * 180f);
            pose.translate(0f, 0.1f, 0.02f);
            pose.scale(0.03f, -0.03f, 0.03f);
            out.submitText(pose, -state.width / 2f, 0, state.label, false, Font.DisplayMode.POLYGON_OFFSET, LightCoordsUtil.FULL_BRIGHT, 0xFF7FC3FF, 0, 0);
            pose.popPose();
        }
    }
}
