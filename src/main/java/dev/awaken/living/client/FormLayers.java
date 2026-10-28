package dev.awaken.living.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import dev.awaken.living.combat.Forms;
import dev.awaken.living.combat.Forms.Species;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/**
 * The forms' eyes on a vanilla body (SPEC §21.11, §21.13): what D wears (the vanilla body, which Fresh Animations or any model pack may
 * animate), and what every rung wears with forms on LITE or while its form body is not built. One layer on each species' vanilla renderer
 * draws, from the creature's form, dark sockets over the vanilla eyes and the light in them (a hot core toward the nose, a rim of the rank's
 * colour, emissive), on the parent model so they follow whatever animates it. From C up on FULL the form body is drawn instead
 * ({@link dev.awaken.living.client.forms.FormBodies}). No mixin: a layer and a render state modifier.
 */
public final class FormLayers {
    private FormLayers() {}

    static final ContextKey<Forms.Form> FORM = new ContextKey<>(Awaken.id("living_form"));

    /** Beyond this (squared) distance the eyes are a pixel: not drawn. */
    private static final double FAR_SQ = 96.0 * 96.0;

    public static void init(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.AddLayers.class, FormLayers::addLayers);
        modBus.addListener(RegisterRenderStateModifiersEvent.class, FormLayers::modifiers);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addLayers(EntityRenderersEvent.AddLayers e) {
        for (Species sp : Species.values()) {
            var renderer = e.getRenderer(sp.type);
            if (renderer instanceof LivingEntityRenderer living) living.addLayer(new EyesLayer(living, sp));
        }
    }

    /** Copies the creature's form into its render state, where the layer reads it. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void modifiers(RegisterRenderStateModifiersEvent e) {
        e.registerEntityModifier((Class) MobRenderer.class, (entity, state) -> {
            Forms.Form f = Forms.of((net.minecraft.world.entity.Entity) entity);
            ((LivingEntityRenderState) state).setRenderData(FORM, f.worn() ? f : null);
        });
    }

    private static boolean exists(Identifier id) {
        return Minecraft.getInstance().getResourceManager().getResource(id).isPresent();
    }

    static final class EyesLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
        private final RenderType sockets;
        /** By rung, null where the texture was not made. */
        private final RenderType[] glow = new RenderType[6];

        EyesLayer(RenderLayerParent<S, M> parent, Species sp) {
            super(parent);
            Identifier o = Awaken.id("textures/entity/forms/" + sp.layout + "/o1.png");
            sockets = exists(o) ? RenderTypes.entityCutoutZOffset(o) : null;
            for (int r = 1; r <= 5; r++) {
                Identifier g = Awaken.id("textures/entity/forms/" + sp.layout + "/g" + r + ".png");
                if (exists(g)) glow[r] = RenderTypes.entityTranslucentEmissive(g);
            }
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, S s, float yRot, float xRot) {
            Forms.Form form = s.getRenderData(FORM);
            if (form == null || s.isInvisible || s.isBaby) return;
            if (LivingClientConfig.formsMode() == 0 || s.distanceToCameraSq > FAR_SQ) return;
            int r = Mth.clamp(form.rung(), 1, 5);
            int hurt = LivingEntityRenderer.getOverlayCoords(s, 0.0F);
            if (sockets != null) collector.order(1).submitModel(getParentModel(), s, pose, sockets, light, hurt, -1, null, s.outlineColor);
            if (glow[r] != null) {
                float a = s.deathTime > 0 ? Math.max(0.0F, 1.0F - s.deathTime / 20.0F) : 1.0F; // the light goes out as it falls
                collector.order(2).submitModel(getParentModel(), s, pose, glow[r], LightCoordsUtil.FULL_BRIGHT, hurt, ARGB.white(a), null, s.outlineColor);
            }
        }
    }
}
