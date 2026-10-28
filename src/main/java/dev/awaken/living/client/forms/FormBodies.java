package dev.awaken.living.client.forms;

import dev.awaken.Awaken;
import dev.awaken.living.LivingNet;
import dev.awaken.living.client.LivingClientConfig;
import dev.awaken.living.combat.Forms;
import dev.awaken.living.combat.Forms.Species;
import dev.awaken.living.forms.FormRigs;
import dev.awaken.mobs.client.anim.HeroRenderState;
import dev.awaken.mobs.client.anim.HeroRig;
import dev.awaken.mobs.client.model.HeroModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The form bodies on screen (SPEC §21.13). For a creature wearing C or higher, with forms on FULL and its body built, a render state
 * modifier on the vanilla renderer fills the creature's own {@link FormBody} through its rig's {@link FormRenderer}; then, when vanilla
 * is about to draw that creature, {@code RenderLivingEvent.Pre} is cancelled and the form body is drawn instead, at the same place (the
 * name tag, fire, outline and shadow stay vanilla's). No mixin. D, LITE, and a creature whose body is missing keep the vanilla body with
 * the forms' eyes ({@code FormLayers}). The client sets the creature's true size when the synced form arrives.
 */
public final class FormBodies {
    private FormBodies() {}

    static final ContextKey<FormBody> BODY = new ContextKey<>(Awaken.id("living_form_body"));
    static final Identifier MISSING = Identifier.withDefaultNamespace("textures/entity/zombie/zombie.png");

    /** Renderers by rig id, built with the entity renderers (a resource reload rebuilds them, and every body with them). */
    private static final Map<String, FormRenderer> RENDERERS = new HashMap<>();
    private static final Map<Entity, FormBody> BODIES = new WeakHashMap<>();
    /** The rung each creature's hitbox was last sized for on this side. */
    private static final Map<Entity, Integer> SIZED = new WeakHashMap<>();
    private static boolean failedOnce;

    public static void init(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class, FormBodies::definitions);
        modBus.addListener(EntityRenderersEvent.AddLayers.class, FormBodies::renderers);
        modBus.addListener(RegisterRenderStateModifiersEvent.class, FormBodies::modifiers);
        NeoForge.EVENT_BUS.addListener(RenderLivingEvent.Pre.class, FormBodies::onRender);
    }

    static ModelLayerLocation layer(String rigId) {
        return new ModelLayerLocation(Awaken.id(rigId), "main");
    }

    private static void definitions(EntityRenderersEvent.RegisterLayerDefinitions e) {
        for (HeroRig rig : FormRigs.all().values()) e.registerLayerDefinition(layer(rig.id), () -> HeroModel.create(rig));
    }

    private static void renderers(EntityRenderersEvent.AddLayers e) {
        RENDERERS.clear();
        BODIES.clear();
        for (HeroRig rig : FormRigs.all().values()) {
            String body = rig.id.substring("form_".length(), rig.id.lastIndexOf('_'));
            int rung = rig.id.charAt(rig.id.length() - 1) - '0';
            try {
                RENDERERS.put(rig.id, new FormRenderer(e.getContext(), rig, body, rung, layer(rig.id)));
            } catch (RuntimeException failed) {
                Awaken.LOG.warn("[forms] body {} did not build, those creatures keep the vanilla body: {}", rig.id, failed.toString());
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void modifiers(RegisterRenderStateModifiersEvent e) {
        e.registerEntityModifier((Class) MobRenderer.class, (entity, state) -> extract((Mob) entity, (LivingEntityRenderState) state));
    }

    static @Nullable FormBody bodyOf(Entity e) {
        return BODIES.get(e);
    }

    /** Called for every mob vanilla draws, after its own state is filled: fills the form body's state, or clears the mark. */
    private static void extract(Mob mob, LivingEntityRenderState state) {
        if (state instanceof HeroRenderState) return; // our own bodies, and AWAKEN's creatures
        Species sp = Species.of(mob.getType());
        if (sp == null) return;
        Forms.Form form = Forms.of(mob);
        size(mob, form.rung());
        FormBody b = null;
        if (form.rung() >= FormRigs.FIRST && !mob.isBaby() && LivingClientConfig.formsMode() == 2) {
            FormRenderer r = RENDERERS.get(FormRigs.id(sp, Math.min(form.rung(), FormRigs.LAST)));
            if (r != null && r.paints(sp)) {
                b = BODIES.get(mob);
                int fam = Math.clamp(form.family(), 0, 2);
                if (b == null || b.renderer != r || b.species != sp || b.family != fam) {
                    b = new FormBody(sp, r.rung, fam, r);
                    BODIES.put(mob, b);
                }
                b.vanilla = state;
                try {
                    r.extractRenderState(mob, b.hero, state.partialTick);
                } catch (RuntimeException failed) {
                    if (!failedOnce) Awaken.LOG.warn("[forms] a form body failed to pose; that creature keeps its vanilla body", failed);
                    failedOnce = true;
                    b = null;
                }
            }
        }
        state.setRenderData(BODY, b);
    }

    /** The synced form came (or changed): the hitbox follows on this side too. */
    private static void size(Mob mob, int rung) {
        Integer was = SIZED.get(mob);
        if (was != null && was == rung) return;
        SIZED.put(mob, rung);
        if (was != null || rung >= 2) mob.refreshDimensions();
    }

    private static void onRender(RenderLivingEvent.Pre<?, ?, ?> e) {
        LivingEntityRenderState state = e.getRenderState();
        if (state instanceof HeroRenderState) return;
        FormBody b = state.getRenderData(BODY);
        if (b == null) return;
        e.setCanceled(true);
        b.renderer.submit(b.hero, e.getPoseStack(), e.getSubmitNodeCollector(),
                Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState);
        b.vanilla = null;
    }

    /** A move the server cued: the creature's body acts it out from {@code startTick} (an empty move lets go). */
    public static void onCue(LivingNet.FormCue m) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity e = level.getEntity(m.entity());
        FormBody b = e == null ? null : BODIES.get(e);
        if (b != null) b.cue(m.move(), m.startTick(), m.windupTicks(), m.holdTicks());
    }
}
