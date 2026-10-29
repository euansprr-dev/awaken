package dev.awaken.kin.works.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.kin.works.DropBlockEntity;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The Drop's heap (SPEC §43.12): what's been tipped in, drawn as real items lying in the bin and piling up as the count
 * grows. Each new stack falls in and settles. At most 24 are drawn up close and 8 from further off (the heap's count is
 * data, not entities), so a full bin costs little.
 */
public final class DropRenderer implements BlockEntityRenderer<DropBlockEntity, DropRenderer.State> {
    static final int NEAR_MAX = 24, FAR_MAX = 8, FAR = 24;
    /** A new stack falls this far over this many ticks, then settles with a small bounce. */
    static final float FALL = 1.1F, FALL_TICKS = 8F;

    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState[] items = new ItemStackRenderState[NEAR_MAX];
        final float[] x = new float[NEAR_MAX], y = new float[NEAR_MAX], z = new float[NEAR_MAX], turn = new float[NEAR_MAX], tilt = new float[NEAR_MAX];
        int n;

        State() {
            for (int i = 0; i < NEAR_MAX; i++) items[i] = new ItemStackRenderState();
        }
    }

    private final ItemModelResolver resolver;

    public DropRenderer(BlockEntityRendererProvider.Context ctx) {
        this.resolver = ctx.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DropBlockEntity be, State s, float partial, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partial, camera, crumbling);
        List<ItemStack> heap = be.heap();
        boolean far = camera.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > FAR * FAR;
        int max = far ? FAR_MAX : NEAR_MAX;
        // the newest on top: draw the last {@code max} stacks of the heap
        int from = Math.max(0, heap.size() - max);
        s.n = 0;
        float now = be.getLevel() == null ? 0F : be.getLevel().getGameTime() + partial;
        long seed = be.getBlockPos().asLong();
        for (int i = from; i < heap.size(); i++) {
            int k = s.n++;
            ItemStack st = heap.get(i);
            resolver.updateForTopItem(s.items[k], st, ItemDisplayContext.GROUND, be.getLevel(), null, (int) seed + i);
            // a fixed place in the bin for each slot of the heap: spread over its floor, rising as it fills
            long h = (seed + i * 0x9E3779B97F4A7C15L) * 0xBF58476D1CE4E5B9L;
            s.x[k] = 0.22F + ((h >>> 8) & 255) / 255F * 0.56F;
            s.z[k] = 0.22F + ((h >>> 16) & 255) / 255F * 0.56F;
            s.turn[k] = ((h >>> 24) & 255) / 255F * 360F;
            s.tilt[k] = (((h >>> 32) & 255) / 255F - 0.5F) * 30F;
            float rest = 0.2F + (i / 6) * 0.055F;
            float age = i < be.seen.size() ? now - be.seen.get(i) : 100F;
            float lift = 0F;
            if (age < FALL_TICKS) {
                float t = 1F - age / FALL_TICKS;
                lift = FALL * t * t;
            } else if (age < FALL_TICKS * 2) {
                float t = (age - FALL_TICKS) / FALL_TICKS;
                lift = 0.06F * (float) Math.sin(t * Math.PI) * (1F - t);
            }
            s.y[k] = rest + lift;
        }
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        for (int k = 0; k < s.n; k++) {
            pose.pushPose();
            pose.translate(s.x[k], s.y[k], s.z[k]);
            pose.rotateDegrees(Axis.YP, s.turn[k]);
            pose.rotateDegrees(Axis.XP, 90F + s.tilt[k]);
            pose.scale(0.6F, 0.6F, 0.6F);
            s.items[k].submit(pose, out, s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
