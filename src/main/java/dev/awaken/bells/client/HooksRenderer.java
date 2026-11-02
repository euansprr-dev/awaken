package dev.awaken.bells.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.bells.BellContent;
import dev.awaken.bells.BellItem;
import dev.awaken.bells.BellLook;
import dev.awaken.bells.HooksEntity;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws each bell hanging on a Bell Frame, Bell Beam or Great Beam (SPEC §43.16) as its item model, from its hook, and
 * swings it when it rings: when it's hung, rung by hand, or struck by the home's song.
 */
public final class HooksRenderer implements BlockEntityRenderer<HooksEntity, HooksRenderer.State> {
    static final int MAX = 4;

    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState[] items = new ItemStackRenderState[MAX];
        final float[][] at = new float[MAX][];
        final float[] swing = new float[MAX];
        final boolean[] has = new boolean[MAX];
        Direction facing = Direction.NORTH;
        int n;
        float scale;

        State() {
            for (int i = 0; i < MAX; i++) items[i] = new ItemStackRenderState();
        }
    }

    /** One stack per bell, made once: nothing is made each frame. */
    private static final ItemStack[] STACKS = new ItemStack[100];

    static ItemStack stack(int no) {
        ItemStack s = STACKS[no];
        if (s == null) STACKS[no] = s = BellItem.of(no);
        return s;
    }

    private final ItemModelResolver items;

    public HooksRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(HooksEntity be, State state, float partialTicks, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, crumbling);
        var bs = be.getBlockState();
        state.facing = bs.hasProperty(HorizontalDirectionalBlock.FACING) ? bs.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        state.n = Math.min(MAX, be.hooks());
        // an S bell's lip just clears its neighbours on the frame and on a beam (stage 6 sizes); a Great Bell a block and a
        // quarter across, so the tower's two hang clear of each other and of the floor
        state.scale = bs.is(BellContent.GREAT_BEAM.get()) ? 1.3F : bs.is(BellContent.BELL_BEAM.get()) ? 0.38F : 0.39F;
        float now = be.getLevel() == null ? 0 : be.getLevel().getGameTime() + partialTicks;
        for (int i = 0; i < state.n; i++) {
            int no = be.bell(i);
            state.has[i] = no >= 0 && no < 100 && be.getLevel() != null;
            if (!state.has[i]) {
                state.items[i].clear();
                continue;
            }
            items.updateForTopItem(state.items[i], stack(no), ItemDisplayContext.NONE, be.getLevel(), null, (int) be.getBlockPos().asLong() + i);
            state.at[i] = hook(bs, i);
            float t = now - Math.max(be.swung[i], SongPlayer.struck(be.getBlockPos(), no));
            // a struck bell swings out and settles over a few seconds
            state.swing[i] = t < 0 || t > 100 ? 0F : (float) (22.0 * Math.exp(-t / 28.0) * Math.sin(t * 0.42));
        }
    }

    private static float[] hook(net.minecraft.world.level.block.state.BlockState s, int i) {
        // the same points the server picks hooks by (bells.Hanging.hookModel)
        if (s.is(BellContent.BELL_FRAME.get())) return new float[]{2.7F + 5.3F * i, 14F, 8F};
        if (s.is(BellContent.BELL_BEAM.get())) return new float[]{2.5F + 3.67F * i, 13F, i % 2 == 0 ? 5.5F : 10.5F};
        return new float[]{8F, 12F, 8F};
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        for (int i = 0; i < state.n; i++) {
            if (!state.has[i]) continue;
            float[] h = state.at[i];
            pose.pushPose();
            pose.translate(0.5F, 0F, 0.5F);
            pose.rotateDegrees(Axis.YP, 180F - state.facing.toYRot());
            pose.translate(h[0] / 16F - 0.5F, h[1] / 16F, h[2] / 16F - 0.5F);
            pose.rotateDegrees(Axis.XP, state.swing[i]);
            pose.scale(state.scale, state.scale, state.scale);
            // the bell model hangs from (8, 15, 8): put that point on the hook
            pose.translate(0F, (8F - BellLook.TOP) / 16F, 0F);
            state.items[i].submit(pose, out, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(HooksEntity be) {
        // a Great Bell hangs well below its beam, and is wider than it
        return new AABB(be.getBlockPos()).inflate(0.5, 0, 0.5).expandTowards(0, -2, 0);
    }
}
