package dev.awaken.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.entity.AwLook;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;

/**
 * Our two layers over any model: the theme skin (moss, rime, brass plates... a cutout over the re-tinted vanilla body)
 * and the emissive glow (eyes, runes, cracks) that brightens while an attack winds up.
 */
final class AwLayers {
    private AwLayers() {}

    static final class Skin<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
        private final Identifier texture;
        private final RenderType cutout, translucent;

        Skin(RenderLayerParent<S, M> parent, MobDef def) {
            super(parent);
            this.texture = AwRender.skin(def);
            this.cutout = RenderTypes.entityCutout(texture);
            this.translucent = RenderTypes.entityTranslucent(texture);
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
            if (state.isInvisible) return;
            AwLook l = AwRender.look(state);
            int alpha = ARGB.alpha(l.tint);
            collector.order(1).submitModel(getParentModel(), state, pose, l.translucent ? translucent : cutout, light,
                    LivingEntityRenderer.getOverlayCoords(state, 0.0F), ARGB.color(alpha, 0xFFFFFF), null, state.outlineColor);
        }
    }

    static final class Glow<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
        private final RenderType type;
        /** The glow with the eyes shut, drawn while the creature blinks (our own bodies with a blink texture). */
        private final @org.jspecify.annotations.Nullable RenderType shut;

        Glow(RenderLayerParent<S, M> parent, MobDef def) {
            super(parent);
            this.type = RenderTypes.entityTranslucentEmissive(AwRender.glow(def));
            var blink = def.model.ownTexture() ? AwRender.exists(AwRender.glowBlink(def)) : null;
            this.shut = blink == null ? null : RenderTypes.entityTranslucentEmissive(blink);
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
            if (state.isInvisible) return;
            AwLook l = AwRender.look(state);
            float a = Math.max(0.0F, Math.min(1.0F, l.glow * (l.translucent ? 0.8F : 1.0F)));
            if (a <= 0.01F) return;
            RenderType type = shut != null && dev.awaken.mobs.client.life.CreatureLife.eyesClosed(dev.awaken.mobs.client.life.Life.from(state)) > 0.5F ? shut : this.type;
            collector.order(2).submitModel(getParentModel(), state, pose, type, LightCoordsUtil.FULL_BRIGHT,
                    LivingEntityRenderer.getOverlayCoords(state, 0.0F), ARGB.white(a), null, state.outlineColor);
        }
    }
}
