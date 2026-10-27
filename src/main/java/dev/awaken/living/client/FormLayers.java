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
 * The forms on screen (SPEC §21.11, decision 18): one layer on each species' vanilla renderer draws, from the creature's form, a skin
 * overlay (the vanilla body again, with scars, rags, plates painted on its own texture layout), the kit (tusks, horns, helms, spines, crowns:
 * a second body of the same vanilla class whose own cubes were swapped for the kit's, so it moves exactly as the creature does) and a
 * glow (a white mask tinted by the rank's colour, emissive). No mixin: only a layer, a render state modifier and layer definitions.
 */
public final class FormLayers {
    private FormLayers() {}

    static final ContextKey<Forms.Form> FORM = new ContextKey<>(Awaken.id("living_form"));

    /** The glow of each rung, the Atlas's rank colours (D gold, C orange, B red, A violet), S a lighter indigo so it reads on the dark. */
    private static final int[] GLOW = {0, 0xE4D063, 0xF29A48, 0xEC5656, 0xB07CFF, 0x8C7BFF};

    /** Beyond these (squared) distances: no overlay, glow or kit; no kit. */
    private static final double FAR_SQ = 96.0 * 96.0, KIT_SQ = 48.0 * 48.0;

    public static void init(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class, FormLayers::definitions);
        modBus.addListener(EntityRenderersEvent.AddLayers.class, FormLayers::addLayers);
        modBus.addListener(RegisterRenderStateModifiersEvent.class, FormLayers::modifiers);
    }

    private static void definitions(EntityRenderersEvent.RegisterLayerDefinitions e) {
        for (Species sp : Species.values())
            for (int r = 1; r <= 5; r++) {
                final int rung = r;
                e.registerLayerDefinition(FormKit.location(sp, rung), () -> FormKit.definition(sp, rung));
            }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addLayers(EntityRenderersEvent.AddLayers e) {
        for (Species sp : Species.values()) {
            var renderer = e.getRenderer(sp.type);
            if (!(renderer instanceof LivingEntityRenderer living)) continue;
            EntityModel<?>[] kit = new EntityModel<?>[6];
            for (int r = 1; r <= 5; r++) {
                try {
                    kit[r] = FormKit.model(sp, e.getEntityModels().bakeLayer(FormKit.location(sp, r)));
                } catch (RuntimeException failed) {
                    Awaken.LOG.warn("[forms] no kit for {} rung {}: {}", sp.id, r, failed.toString());
                }
            }
            living.addLayer(new FormLayer(living, sp, kit));
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

    static final class FormLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
        private final EntityModel<?>[] kit;
        /** [rung][family], null where the texture was not made. */
        private final RenderType[][] overlay = new RenderType[6][3], kitTypes = new RenderType[6][3];
        private final RenderType[] glow = new RenderType[6];

        FormLayer(RenderLayerParent<S, M> parent, Species sp, EntityModel<?>[] kit) {
            super(parent);
            this.kit = kit;
            for (int r = 1; r <= 5; r++) {
                for (int f = 0; f < 3; f++) {
                    Identifier o = Awaken.id("textures/entity/forms/" + sp.layout + "/o" + r + "_" + f + ".png");
                    Identifier k = Awaken.id("textures/entity/forms/" + sp.layout + "/k" + r + "_" + f + ".png");
                    if (exists(o)) overlay[r][f] = RenderTypes.entityCutoutZOffset(o);
                    if (exists(k)) kitTypes[r][f] = RenderTypes.entityCutoutZOffset(k);
                }
                Identifier g = Awaken.id("textures/entity/forms/" + sp.layout + "/g" + r + ".png");
                if (exists(g)) glow[r] = RenderTypes.entityTranslucentEmissive(g);
            }
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, S s, float yRot, float xRot) {
            Forms.Form form = s.getRenderData(FORM);
            if (form == null || s.isInvisible || s.isBaby) return;
            int mode = LivingClientConfig.formsMode();
            if (mode == 0 || s.distanceToCameraSq > FAR_SQ) return;
            int r = Mth.clamp(form.rung(), 1, 5), fam = Mth.clamp(form.family(), 0, 2);
            int hurt = LivingEntityRenderer.getOverlayCoords(s, 0.0F);
            if (overlay[r][fam] != null) collector.order(1).submitModel(getParentModel(), s, pose, overlay[r][fam], light, hurt, -1, null, s.outlineColor);
            if (mode == 2 && s.distanceToCameraSq <= KIT_SQ && kit[r] != null && kitTypes[r][fam] != null)
                collector.order(1).submitModel((EntityModel) kit[r], s, pose, kitTypes[r][fam], light, hurt, -1, null, s.outlineColor);
            if (glow[r] != null) {
                float pulse = r >= 3 ? 0.8F + 0.2F * Mth.sin(s.ageInTicks * 0.13F) : 1.0F;
                collector.order(2).submitModel(getParentModel(), s, pose, glow[r], LightCoordsUtil.FULL_BRIGHT, hurt, ARGB.color(Math.round(255 * pulse), GLOW[r]), null, s.outlineColor);
            }
        }
    }
}
