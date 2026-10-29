package dev.awaken.world.stilled.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.world.stilled.StilledBlockEntity;
import dev.awaken.world.stilled.StilledText;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/** Draws a Stilled (SPEC §43.3): the figure (twice for a pair or a parent with a child) turned the way they face. */
public final class StilledRenderer implements BlockEntityRenderer<StilledBlockEntity, StilledRenderer.State> {
    /** The child beside a grown-up: this much smaller, this far to the side (model pixels). */
    static final float KID = 0.62F, KID_SIDE = 9.5F;
    private static final Map<StilledText.Look, Identifier> TEXTURES = new EnumMap<>(StilledText.Look.class);

    static {
        for (StilledText.Look l : StilledText.Look.values()) TEXTURES.put(l, Awaken.id("textures/entity/stilled/" + l.texture() + ".png"));
    }

    public static final class State extends BlockEntityRenderState {
        StilledText.Pose pose = StilledText.Pose.WALKING;
        StilledText.Look look = StilledText.Look.MAN, look2 = StilledText.Look.WOMAN;
        float yaw;
    }

    private final StilledModel model;

    public StilledRenderer(BlockEntityRendererProvider.Context ctx) {
        model = new StilledModel(ctx.entityModelSet().bakeLayer(StilledModel.LAYER));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(StilledBlockEntity be, State state, float partialTicks, Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, crumbling);
        state.pose = be.pose();
        state.look = be.look();
        state.look2 = be.look2();
        state.yaw = be.yaw();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        pose.pushPose();
        // as a living entity is drawn: turned to its yaw, then into model space (y down, feet at 24 pixels)
        pose.translate(0.5F, 0F, 0.5F);
        pose.rotate(Axis.YP.rotationDegrees(180F - state.yaw));
        pose.scale(-1F, -1F, 1F);
        pose.translate(0F, -1.501F, 0F);
        out.submitModel(model, new StilledModel.Figure(state.pose, false), pose, TEXTURES.get(state.look), state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        if (state.pose.two()) {
            pose.pushPose();
            if (state.pose == StilledText.Pose.CHILD) {
                pose.translate(KID_SIDE / 16F, 1.5F * (1F - KID), 0F);
                pose.scale(KID, KID, KID);
            }
            out.submitModel(model, new StilledModel.Figure(state.pose, true), pose, TEXTURES.get(state.look2), state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        pose.popPose();
    }

    /** Seen from further than a chest: they stand in open country, and only a few are ever near. */
    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(StilledBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(1.2, 0, 1.2).expandTowards(0, 1.2, 0);
    }
}
