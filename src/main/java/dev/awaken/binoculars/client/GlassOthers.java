package dev.awaken.binoculars.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.binoculars.BinocularsModule;
import dev.awaken.binoculars.BinocularsNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/**
 * What other players see of your binoculars, and you of theirs (SPEC §39.6): both arms raised to the eyes (the
 * {@code AWAKEN_BINOCULARS} arm pose, META-INF/enumextensions.json) and the binoculars held at the face (a render
 * layer). States arrive through {@code awaken:binoculars_relay} and blend over 250 ms. Your own shows only in a view
 * of yourself ({@code /awaken binoculars peek}); raising always looks through your own eyes.
 */
public final class GlassOthers {
    private GlassOthers() {}

    /** Read by NeoForge's enum extender (the json names this field). */
    public static final EnumProxy<HumanoidModel.ArmPose> POSE = new EnumProxy<>(HumanoidModel.ArmPose.class,
            false, false, (IArmPoseTransformer) GlassOthers::apply);

    private static final class Raise {
        int entity = -1;
        boolean up;
        float progress;
    }

    private static final Raise[] SLOTS = new Raise[64];
    private static final Raise SELF = new Raise();
    static final ContextKey<Raise> KEY = new ContextKey<>(Awaken.id("binoculars_raise"));
    /** {@code /awaken binoculars peek}: until then (nanoTime), your own player shows them up in third person. */
    static long peekUntil;

    static {
        for (int i = 0; i < SLOTS.length; i++) SLOTS[i] = new Raise();
    }

    static void onRelay(BinocularsNet.Relay msg) {
        Raise free = null;
        for (Raise r : SLOTS) {
            if (r.entity == msg.entityId()) {
                r.up = msg.up();
                return;
            }
            if (free == null && r.entity < 0) free = r;
        }
        if (free != null && msg.up()) {
            free.entity = msg.entityId();
            free.up = true;
            free.progress = 0;
        }
    }

    static void clear() {
        for (Raise r : SLOTS) {
            r.entity = -1;
            r.up = false;
            r.progress = 0;
        }
        peekUntil = 0;
    }

    /** Once a frame: ease every raise toward its state; free slots that have fully lowered. */
    static void advance(float dtMs) {
        float step = dtMs / 250F;
        for (Raise r : SLOTS) {
            if (r.entity < 0) continue;
            r.progress = r.up ? Math.min(1, r.progress + step) : Math.max(0, r.progress - step);
            if (!r.up && r.progress <= 0) r.entity = -1;
        }
        boolean peek = System.nanoTime() < peekUntil;
        SELF.progress = peek ? Math.min(1, SELF.progress + step) : 0;
    }

    private static Raise find(int entity) {
        for (Raise r : SLOTS) if (r.entity == entity) return r;
        return null;
    }

    static float progress(EntityRenderState state) {
        Raise r = state.getRenderData(KEY);
        if (r == null) return 0;
        float p = r.progress;
        return p * p * (3 - 2 * p);
    }

    /** Render state modifier: the raise on the state and the pose on both arms. */
    static void modify(Entity entity, EntityRenderState state) {
        if (!(state instanceof HumanoidRenderState s) || !(entity instanceof AbstractClientPlayer player)) return;
        Raise r = player == Minecraft.getInstance().player ? (SELF.progress > 0 ? SELF : null) : find(player.getId());
        s.setRenderData(KEY, r);
        if (r == null || r.progress <= 0) return;
        s.rightArmPose = POSE.getValue();
        s.leftArmPose = POSE.getValue();
    }

    /** Both hands to the eyes, elbows out, following the head (vanilla's spyglass pose, for two hands). */
    private static void apply(HumanoidModel<?> model, HumanoidRenderState state, HumanoidArm arm) {
        float p = progress(state);
        if (p <= 0) return;
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        var part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        // crouching adds 0.4 to each arm after the poses: take it back so the hands stay at the eyes
        float x = model.head.xRot - 1.92F - (state.isCrouching ? 0.4F : 0F);
        part.xRot = Mth.lerp(p, part.xRot, x);
        part.yRot = Mth.lerp(p, part.yRot, model.head.yRot - side * 0.48F);
        part.zRot = Mth.lerp(p, part.zRot, side * 0.12F);
    }

    // ---- the binoculars at the face ----------------------------------------------------------------------------------

    private static final ItemStackRenderState ITEM = new ItemStackRenderState();
    private static ItemStack stack = ItemStack.EMPTY;

    static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
        Layer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack ps, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
            float p = progress(state);
            if (p <= 0.02F || state.isInvisible) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            if (stack.isEmpty()) stack = new ItemStack(BinocularsModule.BINOCULARS.get());
            // every pair looks the same: one shared state is safe even if the collector draws later
            mc.getItemModelResolver().updateForTopItem(ITEM, stack, ItemDisplayContext.NONE, mc.level, null, 0);
            ps.pushPose();
            getParentModel().head.translateAndRotate(ps);
            // in front of the eyes (model space: y down, -z the face's front), coming up from the chest as it blends in
            ps.translate(0, (-4.2F + 6F * (1 - p)) / 16F, -5.4F / 16F);
            ps.rotateDegrees(Axis.YP, 180);
            ps.scale(0.42F, -0.42F, -0.42F);
            ITEM.submit(ps, collector, light, OverlayTexture.NO_OVERLAY, 0);
            ps.popPose();
        }
    }
}
