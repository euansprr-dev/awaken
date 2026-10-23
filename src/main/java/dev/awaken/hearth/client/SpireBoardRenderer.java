package dev.awaken.hearth.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.hearth.block.SpireBoardBlock;
import dev.awaken.hearth.block.SpireBoardBlockEntity;
import dev.awaken.progression.Rank;
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

import java.util.ArrayList;
import java.util.List;

/** The Spire Board's face: the hunter's name, rank in its colour, level, and a few records. Glows a little. */
public final class SpireBoardRenderer implements BlockEntityRenderer<SpireBoardBlockEntity, SpireBoardRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        final List<FormattedCharSequence> lines = new ArrayList<>();
        final List<Integer> colours = new ArrayList<>();
        final List<Integer> widths = new ArrayList<>();
    }

    private static final float SCALE = 0.0085f;
    private final Font font;

    public SpireBoardRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.font();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SpireBoardBlockEntity be, State state, float partialTicks, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, crumbling);
        state.facing = be.getBlockState().getValue(SpireBoardBlock.FACING);
        state.lines.clear();
        state.colours.clear();
        state.widths.clear();
        if (be.name().isEmpty()) {
            add(state, "THE SPIRE", 0xFF7FC3FF);
            add(state, "unclaimed", 0xFF8FA7C4);
            return;
        }
        Rank rank = Rank.of(be.rank());
        add(state, be.name(), 0xFFFFFFFF);
        add(state, rank.label + "-RANK", 0xFF000000 | rank.colour);
        add(state, "Level " + be.hunterLevel(), 0xFFD9E4F2);
        for (String l : be.lines()) add(state, l, 0xFFFFCF7A);
    }

    private void add(State s, String text, int colour) {
        FormattedCharSequence seq = Component.literal(text).getVisualOrderText();
        s.lines.add(seq);
        s.colours.add(colour);
        s.widths.add(font.width(seq));
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (state.lines.isEmpty()) return;
        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        pose.rotateDegrees(Axis.YP, -state.facing.toYRot());
        pose.translate(0f, 0.43f, 0.131f);
        pose.scale(SCALE, -SCALE, SCALE);
        int light = LightCoordsUtil.pack(12, 15);
        int y = 0;
        for (int i = 0; i < state.lines.size() && i < 9; i++) {
            int w = Math.min(state.widths.get(i), 100);
            out.submitText(pose, -w / 2f, y, state.lines.get(i), false, Font.DisplayMode.POLYGON_OFFSET, light, state.colours.get(i), 0, 0);
            y += i < 3 ? 10 : 9;
        }
        pose.popPose();
    }
}
