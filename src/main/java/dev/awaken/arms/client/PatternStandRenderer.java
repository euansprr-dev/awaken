package dev.awaken.arms.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.arms.ArmsHomes;
import dev.awaken.arms.ArmsStands;
import dev.awaken.arms.PatternStandBlock;
import dev.awaken.arms.PatternStandBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import dev.awaken.progression.Rank;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Hangs the tier's weapon above a Pattern Stand, turning slowly, so the stand shows what its pattern is for. */
public final class PatternStandRenderer implements BlockEntityRenderer<PatternStandBlockEntity, PatternStandRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        /** Hale's rack: the weapons on its hooks, and the way it faces. */
        final ItemStackRenderState[] hooks = {new ItemStackRenderState(), new ItemStackRenderState(), new ItemStackRenderState()};
        final boolean[] hung = new boolean[3];
        Direction facing = Direction.NORTH;
        boolean has, hale;
        float lift, age;
    }

    private final ItemModelResolver items;

    public PatternStandRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PatternStandBlockEntity be, State state, float partialTicks, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, crumbling);
        state.hale = be.style().equals(ArmsStands.HALE);
        if (state.hale && be.getLevel() != null) {
            // Hale's rack: the Wildhide weapons, three to a rack in the order of his list
            state.facing = be.getBlockState().getValue(PatternStandBlock.FACING);
            var e = ArmsHomes.atRank(Rank.E);
            for (int i = 0; i < 3; i++) {
                int at = be.tier() + i;
                ItemStack w = at < e.size() ? ArmsStands.weapon(e.get(at).style(), 0) : ItemStack.EMPTY;
                state.hung[i] = !w.isEmpty();
                if (state.hung[i]) items.updateForTopItem(state.hooks[i], w, ItemDisplayContext.FIXED, be.getLevel(), null, (int) be.getBlockPos().asLong() + i);
                else state.hooks[i].clear();
            }
            state.has = false;
            return;
        }
        ItemStack weapon = be.style().isEmpty() ? ItemStack.EMPTY : ArmsStands.weapon(be.style(), be.tier());
        state.has = !weapon.isEmpty() && be.getLevel() != null;
        if (!state.has) {
            state.item.clear();
            return;
        }
        items.updateForTopItem(state.item, weapon, ItemDisplayContext.FIXED, be.getLevel(), null, (int) be.getBlockPos().asLong());
        state.lift = be.getBlockState().getValue(PatternStandBlock.KIND).lift;
        state.age = be.getLevel().getGameTime() + partialTicks;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (state.hale) {
            for (int i = 0; i < 3; i++) {
                if (!state.hung[i]) continue;
                pose.pushPose();
                pose.translate(0.5f, 0.56f, 0.5f);
                pose.rotateDegrees(Axis.YP, 180f - state.facing.toYRot());
                pose.translate((i - 1) * 0.3f, 0f, -0.16f);
                pose.rotateDegrees(Axis.ZP, -35f);
                pose.scale(0.55f, 0.55f, 0.55f);
                state.hooks[i].submit(pose, out, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
            return;
        }
        if (!state.has) return;
        pose.pushPose();
        pose.translate(0.5f, state.lift + (float) Math.sin(state.age * 0.06f) * 0.04f, 0.5f);
        pose.rotateDegrees(Axis.YP, state.age * 1.5f);
        pose.scale(0.85f, 0.85f, 0.85f);
        state.item.submit(pose, out, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
