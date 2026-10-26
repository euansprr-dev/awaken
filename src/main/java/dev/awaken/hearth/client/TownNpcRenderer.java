package dev.awaken.hearth.client;

import dev.awaken.Awaken;
import dev.awaken.core.client.skin.SkinRenderState;
import dev.awaken.core.client.skin.SkinnedMobRenderer;
import dev.awaken.hearth.town.TownNpc;
import dev.awaken.hearth.town.Townsfolk;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Townsfolk use the player model with their own generated skins (textures/entity/townsfolk/&lt;name&gt;.png). */
public final class TownNpcRenderer extends SkinnedMobRenderer<TownNpc, SkinRenderState> {
    private static final Map<String, Identifier> SKINS = new HashMap<>();
    private static final Identifier FALLBACK = Awaken.id("textures/entity/townsfolk/oren.png");

    private static final Map<String, Identifier> WORN = new HashMap<>();
    private static final net.minecraft.world.item.ItemStack BUNDLE = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BUNDLE);

    public TownNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, 0.5f);
        addLayer(new BundleLayer(this));
    }

    @Override
    public SkinRenderState createRenderState() {
        return new State();
    }

    /** (W6) Someone walking up the road to move in carries their things in a bundle on their back. */
    public static final class State extends SkinRenderState {
        public boolean bundle;
        public final net.minecraft.client.renderer.item.ItemStackRenderState back = new net.minecraft.client.renderer.item.ItemStackRenderState();
    }

    @Override
    public void extractRenderState(TownNpc npc, SkinRenderState state, float partialTicks) {
        super.extractRenderState(npc, state, partialTicks);
        String worn = npc.skin();
        boolean slim = (npc.looks() & TownNpc.L_SLIM) != 0;
        state.wear(worn.isEmpty() ? skin(npc.kind()) : WORN.computeIfAbsent(worn, w -> Awaken.id("textures/entity/" + w + ".png")), slim);
        if (state instanceof State s) {
            s.bundle = (npc.looks() & TownNpc.L_BUNDLE) != 0;
            if (s.bundle) itemModelResolver.updateForLiving(s.back, BUNDLE, net.minecraft.world.item.ItemDisplayContext.NONE, npc);
            else s.back.clear();
        }
    }

    /** The bundle strapped high between the shoulders (the city porters' crate, in the townsfolk's own mesh). */
    private static final class BundleLayer extends net.minecraft.client.renderer.entity.layers.RenderLayer<SkinRenderState, dev.awaken.core.client.skin.SkinModel<SkinRenderState>> {
        BundleLayer(net.minecraft.client.renderer.entity.RenderLayerParent<SkinRenderState, dev.awaken.core.client.skin.SkinModel<SkinRenderState>> parent) {
            super(parent);
        }

        @Override
        public void submit(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector, int light,
                           SkinRenderState state, float yRot, float xRot) {
            if (!(state instanceof State s) || !s.bundle || s.back.isEmpty()) return;
            pose.pushPose();
            var m = getParentModel();
            m.root().translateAndRotate(pose);
            m.body.translateAndRotate(pose);
            // between the shoulder blades (+z is the back here, as for the cape), a bundle a little wider than the torso
            pose.translate(0f, 0.36f, 0.32f);
            pose.rotateAround(com.mojang.math.Axis.XP.rotationDegrees(-8f), 0f, 0f, 0f);
            pose.scale(0.95f, 0.95f, 0.95f);
            s.back.submit(pose, collector, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, s.outlineColor);
            pose.popPose();
        }
    }

    /** "oren_blacksmith" → textures/entity/townsfolk/oren.png, resolved once per kind. */
    private static Identifier skin(String kind) {
        if (kind.isEmpty()) return FALLBACK;
        return SKINS.computeIfAbsent(kind, k -> Awaken.id("textures/entity/townsfolk/" + Townsfolk.skin(k) + ".png"));
    }
}
