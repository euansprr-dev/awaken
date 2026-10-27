package dev.awaken.gear.premium.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The premium test's draw layer on players and mannequins (the pattern of the Ascent's wings and the mobs' glow layer): at level 1 the
 * emissive pass over the model vanilla's layer draws; at level 2 the whole set, drawn by the armour shader (the vanilla layer is skipped).
 * Everything it needs is in the render state: what is worn and how much of the set.
 */
final class PremiumLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private PremiumLayer(AvatarRenderer<?> parent) {
        super(parent);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void onAddLayers(EntityRenderersEvent.AddLayers e) {
        for (var type : e.getSkins()) {
            AvatarRenderer<?> players = e.getPlayerRenderer(type);
            if (players != null) ((AvatarRenderer) players).addLayer(new PremiumLayer(players));
            AvatarRenderer<?> mannequins = e.getMannequinRenderer(type);
            if (mannequins != null) ((AvatarRenderer) mannequins).addLayer(new PremiumLayer(mannequins));
        }
    }

    private static ItemStack stackOf(AvatarRenderState s, EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> s.headEquipment;
            case CHEST -> s.chestEquipment;
            case LEGS -> s.legsEquipment;
            default -> s.feetEquipment;
        };
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        int fidelity = PremiumArmour.fidelity;
        if (fidelity < 1 || state.isInvisible) return;
        if (fidelity == PremiumArmour.HERO) {
            // the hero hood alone (pack/armour-hero-piece): our own mesh on the head; the other pieces draw as they ship
            float[] mesh = PremiumArmour.isHero(state.headEquipment) ? HeroHood.data() : null;
            if (mesh == null) return;
            pose.pushPose();
            getParentModel().root().translateAndRotate(pose);
            getParentModel().translateToHead(pose);
            collector.order(1).submitCustomGeometry(pose, PremiumPipelines.hero(HeroHood.TEXTURE), (p, out) -> HeroHood.emit(p, out, mesh, light));
            pose.popPose();
            return;
        }
        EquipmentSlot[] slots = PremiumArmour.slots();
        int worn = 0;
        for (EquipmentSlot slot : slots) if (PremiumArmour.isHero(stackOf(state, slot))) worn++;
        if (worn == 0) return;
        // the set's level: a stir with one piece, the half bonus with two, the whole set with four (the plan's rule for glow)
        float level = worn >= 4 ? 1.0F : worn >= 2 ? 0.55F : 0.35F;
        boolean shader = fidelity >= 2;
        boolean plain = shader && !PremiumPipelines.ok();
        for (EquipmentSlot slot : slots) {
            if (!PremiumArmour.isHero(stackOf(state, slot))) continue;
            HumanoidModel<HumanoidRenderState> model = PremiumArmour.model(slot);
            if (model == null) continue;
            if (shader && !plain) {
                collector.order(1).submitModel(model, state, pose, PremiumPipelines.armour(PremiumArmour.TEXTURE), light, OverlayTexture.NO_OVERLAY,
                        ARGB.color(Math.round(level * 255), PremiumArmour.ACCENT), null, state.outlineColor);
            } else {
                if (plain) {
                    // the shader failed: the planned look's body, since vanilla's layer was skipped at level 2
                    collector.order(1).submitModel(model, state, pose, PremiumPipelines.plain(PremiumArmour.TEXTURE), light, OverlayTexture.NO_OVERLAY,
                            -1, null, state.outlineColor);
                }
                // a cutout pass at full light, not the translucent emissive type: that one goes through the order-independent
                // path, which drew nothing here on CI's Vulkan (Look A, 2026-10-27)
                RenderType glow = RenderTypes.armorCutoutNoCull(PremiumArmour.GLOW);
                int v = Math.round(level * 255);
                collector.order(2).submitModel(model, state, pose, glow, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        ARGB.color(255, v, v, v), null, state.outlineColor);
            }
        }
    }
}
