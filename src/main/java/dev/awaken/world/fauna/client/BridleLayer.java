package dev.awaken.world.fauna.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.Awaken;
import dev.awaken.mobs.entity.WildMount;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/**
 * The bridle on the Frost Stag and the Dune Ray (their bodies are P1's models): the body drawn once more with a strap
 * texture painted on its own UV layout ({@code textures/entity/mobs/<id>_bridle.png}, tools/art/specs/fauna3.py), only
 * when the mount wears one. The Tidesteed's bridle is part of its rig (bones named bridle*).
 */
final class BridleLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
    static final ContextKey<Boolean> BRIDLED = new ContextKey<>(Awaken.id("fauna_bridled"));

    private final RenderType type;

    BridleLayer(RenderLayerParent<S, M> parent, Identifier texture) {
        super(parent);
        this.type = RenderTypes.entityCutoutZOffset(texture);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, S s, float yRot, float xRot) {
        if (s.isInvisible || !Boolean.TRUE.equals(s.getRenderData(BRIDLED))) return;
        collector.order(1).submitModel(getParentModel(), s, pose, type, light, LivingEntityRenderer.getOverlayCoords(s, 0.0F), -1, null, s.outlineColor);
    }

    /** Adds the layer to the two mounts' renderers (they are built by the mobs module's AwRenderers). */
    static void addLayers(EntityRenderersEvent.AddLayers e) {
        for (String id : java.util.List.of("frost_stag", "dune_ray")) {
            EntityType<?> type = dev.awaken.mobs.MobTypes.type(id);
            if (type == null) continue;
            var renderer = e.getRenderer(type);
            if (renderer instanceof LivingEntityRenderer<?, ?, ?> living) add(living, Awaken.id("textures/entity/mobs/" + id + "_bridle.png"));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void add(LivingEntityRenderer living, Identifier texture) {
        living.addLayer(new BridleLayer(living, texture));
    }

    /** Copies "wears a bridle" from the entity into every mob renderer's state (read by the layer above). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void modifiers(RegisterRenderStateModifiersEvent e) {
        e.registerEntityModifier((Class) MobRenderer.class, (entity, state) -> {
            if (entity instanceof WildMount m && m.kind() != null) ((LivingEntityRenderState) state).setRenderData(BRIDLED, m.bridled());
        });
    }
}
